import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import {
  LatestRequestGate,
  availabilityView,
  formatInquiryDeadline,
  isRequestCancellation,
  loadPublicResultsAndInquiry,
  resolvePublicResultsAndInquiry,
  shouldRequestResultInquiry,
} from '../src/utils/resultInquiry.ts'

const eventResultsSource = await readFile(new URL('../src/components/EventResults.tsx', import.meta.url), 'utf8')
const panelSource = await readFile(new URL('../src/components/ResultInquiryPanel.tsx', import.meta.url), 'utf8')
const dialogSource = await readFile(new URL('../src/components/ResultIssueDialog.tsx', import.meta.url), 'utf8')
const detailsSource = await readFile(new URL('../src/components/ResultDetailsDialog.tsx', import.meta.url), 'utf8')
const appealSource = await readFile(new URL('../src/pages/AppealPage.tsx', import.meta.url), 'utf8')
const clientSource = await readFile(new URL('../src/api/client.ts', import.meta.url), 'utf8')
const typesSource = await readFile(new URL('../src/api/types.ts', import.meta.url), 'utf8')
const journalSource = await readFile(new URL('../src/admin/pages/AdminJournalPage.tsx', import.meta.url), 'utf8')
const adminResultEditorSource = await readFile(new URL('../src/admin/components/AdminResultEditor.tsx', import.meta.url), 'utf8')

test('only an explicit bib filter is eligible for Result Inquiry recovery', () => {
  assert.equal(shouldRequestResultInquiry({ bib: '101' }), true)
  assert.equal(shouldRequestResultInquiry({ name: 'Иван Иванов' }), false)
  assert.equal(shouldRequestResultInquiry({ bib: '101', name: 'Иван' }), true)
  assert.match(eventResultsSource, /loadPublicResultsAndInquiry\([\s\S]*api\.results[\s\S]*api\.resultInquiry/)
})

test('exact-bib recovery checks registration only after the public protocol returns zero rows', async () => {
  for (const publicRowCount of [0, 1, 3]) {
    const calls = []
    const loaded = await loadPublicResultsAndInquiry(
      { bib: '1100' },
      async () => { calls.push('public-results'); return { content: Array(publicRowCount).fill({}) } },
      async () => {
        calls.push('result-inquiry')
        return {
          lookupState: 'AMBIGUOUS', inquiryAvailability: 'OPEN', bib: '1100',
          missingResultActionAvailable: false, candidates: [],
        }
      },
    )
    assert.deepEqual(calls, publicRowCount === 0
      ? ['public-results', 'result-inquiry']
      : ['public-results'])
    assert.equal(loaded.publicResults.status, 'fulfilled')
    assert.equal(loaded.inquiry.status, 'fulfilled')
    if (loaded.inquiry.status === 'fulfilled') {
      assert.equal(loaded.inquiry.value?.lookupState, publicRowCount === 0 ? 'AMBIGUOUS' : undefined)
    }
  }
})

test('exact-bib recovery waits for an empty public result before calling Result Inquiry', async () => {
  const calls = []
  const resultsResponse = deferred()
  const inquiryResponse = deferred()
  let completed = false
  const loading = loadPublicResultsAndInquiry(
    { bib: '1100' },
    () => { calls.push('public-results'); return resultsResponse.promise },
    () => { calls.push('result-inquiry'); return inquiryResponse.promise },
  ).then((outcome) => { completed = true; return outcome })

  assert.deepEqual(calls, ['public-results'])
  resultsResponse.resolve({ content: [] })
  await new Promise((resolve) => setImmediate(resolve))
  assert.equal(completed, false)
  assert.deepEqual(calls, ['public-results', 'result-inquiry'])
  inquiryResponse.resolve({
    lookupState: 'RESULT_NOT_PUBLIC', inquiryAvailability: 'OPEN', bib: '1100',
    missingResultActionAvailable: true, candidates: [],
  })
  const loaded = await loading
  assert.equal(loaded.inquiry.status, 'fulfilled')
})

test('name search performs only the public result request', async () => {
  const calls = []
  const loaded = await loadPublicResultsAndInquiry(
    { name: 'Иван' },
    async () => { calls.push('public-results'); return { content: [] } },
    async () => { calls.push('result-inquiry'); throw new Error('must not run') },
  )
  assert.deepEqual(calls, ['public-results'])
  assert.equal(loaded.inquiry.status, 'fulfilled')
})

test('latest request gate ignores stale exact-bib searches', () => {
  const gate = new LatestRequestGate()
  const first = gate.begin()
  const second = gate.begin()
  assert.equal(gate.isCurrent(first), false)
  assert.equal(gate.isCurrent(second), true)
  gate.invalidate()
  assert.equal(gate.isCurrent(second), false)
})

test('race or Event change invalidates previous inquiry state', () => {
  assert.match(eventResultsSource, /event\.id, raceId/)
  assert.match(eventResultsSource, /api\.resultInquiry\(event\.id, debouncedBib\.trim\(\), Number\(raceId\)/)
  assert.doesNotMatch(eventResultsSource, /sportFormatId/)
})

test('cancellation is silent while real and partial failures remain distinguishable', () => {
  const abortError = new Error('aborted')
  abortError.name = 'AbortError'
  const networkError = new Error('network down')
  assert.equal(isRequestCancellation(abortError), true)
  assert.equal(isRequestCancellation(networkError), false)
  assert.deepEqual(resolvePublicResultsAndInquiry({
    publicResults: { status: 'rejected', reason: abortError },
    inquiry: { status: 'fulfilled', value: null },
  }), { state: 'CANCELLED' })
  const partial = resolvePublicResultsAndInquiry({
    publicResults: { status: 'fulfilled', value: { content: [] } },
    inquiry: { status: 'rejected', reason: networkError },
  })
  assert.equal(partial.state, 'PUBLIC_RESULTS_LOADED')
  assert.equal(partial.inquiryError, networkError)
})

test('availability remains backend-driven after removing identity verification', () => {
  assert.equal(availabilityView('OPEN', undefined, 'UTC').canContact, true)
  for (const state of ['CLOSED', 'NOT_OPEN_YET', 'DISABLED']) {
    assert.equal(availabilityView(state, undefined, 'UTC').canContact, false)
  }
  assert.match(formatInquiryDeadline('2026-09-27T21:00:00Z', 'Europe/Moscow') ?? '', /28\.09\.2026/)
})

test('Result Inquiry API uses event, bib and optional race without a verification endpoint', async () => {
  const calls = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (input, init) => {
    calls.push({ input: String(input), init })
    return new Response(JSON.stringify({
      lookupState: 'RESULT_NOT_PUBLIC', inquiryAvailability: 'OPEN', bib: '1100',
      missingResultActionAvailable: true, candidates: [],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  try {
    const { api } = await import(`../src/api/client.ts?no-dob-inquiry=${Date.now()}`)
    await api.resultInquiry(7, '1100', 23)
  } finally {
    globalThis.fetch = originalFetch
  }
  assert.equal(calls[0].input, '/api/events/7/result-inquiry?bib=1100&raceId=23')
  assert.equal(calls[0].init?.method, undefined)
  assert.doesNotMatch(clientSource, /verifyResultInquiry|result-inquiry\/verify/)
})

test('typed API contract represents duplicate bib ambiguity explicitly and safely', () => {
  const inquiryContract = typesSource.slice(
    typesSource.indexOf('export interface ResultInquiryLookup'),
    typesSource.indexOf('export type ResultIssueType'),
  )
  assert.match(typesSource, /'AMBIGUOUS'/)
  assert.match(typesSource, /candidates: Array<\{/)
  assert.match(typesSource, /raceId: number/)
  assert.match(typesSource, /raceDisplayName: string/)
  assert.doesNotMatch(inquiryContract, /birthDate|contactEmail|activeIssue|issueId|phone/)
  assert.doesNotMatch(typesSource, /VerifyResultInquiryRequest/)
})

test('non-public participant identity is omitted while public result identity remains available', () => {
  assert.match(dialogSource, /inquiry\.participantDisplayName \|\| props\.kind === 'RESULT_CORRECTION'/)
  assert.match(panelSource, /inquiry\.participantDisplayName &&/)
  assert.doesNotMatch(dialogSource, /\? props\.result\.displayName : '—'/)
})

test('duplicate bib renders a server-provided race selector and sends the selected race back', () => {
  assert.match(panelSource, /lookupState === 'AMBIGUOUS'/)
  assert.match(panelSource, /Выберите дистанцию \/ формат/)
  assert.match(panelSource, /inquiry\.candidates\.map/)
  assert.match(panelSource, /inquiry\.candidates\.length > 1/)
  assert.match(panelSource, /нескольких участников одного старта/)
  assert.match(panelSource, /onSelectRace\(candidate\.raceId\)/)
  assert.match(appealSource, /onSelectRace=\{runLookup\}/)
  assert.match(appealSource, /api\.resultInquiry\(event\.id, bib\.trim\(\), raceId\)/)
})

test('unique result and missing-result flows open the existing issue dialog without DOB', () => {
  assert.match(appealSource, /lookupState === 'RESULT_PUBLIC'/)
  assert.match(appealSource, /Сообщить об ошибке/)
  assert.match(panelSource, /kind="MISSING_RESULT"/)
  assert.match(dialogSource, /createMissingResultIssue|createResultCorrectionIssue/)
  assert.doesNotMatch(`${appealSource}\n${panelSource}\n${dialogSource}`, /birthDate|Дата рождения|ДД\.ММ\.ГГГГ|verifyResultInquiry/)
})

test('public result detail scopes inquiry by the loaded race and exposes correction only when open', () => {
  assert.match(detailsSource, /loadedResult\.eventId, loadedResult\.bib, loadedResult\.raceId/)
  assert.match(detailsSource, /inquiry\?\.inquiryAvailability === 'OPEN'/)
  assert.match(detailsSource, /Сообщить об ошибке/)
})

test('DOB remains in admin and operational journal domain while absent from public appeal', () => {
  assert.match(journalSource, /birthDate|Дата рождения/)
  assert.match(adminResultEditorSource, /birthDate|Дата рождения/)
  assert.doesNotMatch(`${appealSource}\n${panelSource}\n${dialogSource}`, /birthDate|Дата рождения/)
})

test('issue modal preserves accessible dismissal and focus handling', () => {
  assert.match(dialogSource, /role="dialog"/)
  assert.match(dialogSource, /aria-modal="true"/)
  assert.match(dialogSource, /event\.key === 'Escape'/)
  assert.match(dialogSource, /event\.key !== 'Tab'/)
})

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}
