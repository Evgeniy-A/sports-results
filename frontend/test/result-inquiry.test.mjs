import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import {
  LatestRequestGate,
  availabilityView,
  buildInquiryMailto,
  createInquiryEmailTemplates,
  formatInquiryDeadline,
  isRequestCancellation,
  loadPublicResultsAndInquiry,
  parseRussianBirthDate,
  resolvePublicResultsAndInquiry,
  shouldRequestResultInquiry,
} from '../src/utils/resultInquiry.ts'

const eventResultsSource = await readFile(new URL('../src/components/EventResults.tsx', import.meta.url), 'utf8')
const panelSource = await readFile(new URL('../src/components/ResultInquiryPanel.tsx', import.meta.url), 'utf8')
const dialogSource = await readFile(new URL('../src/components/ResultIssueDialog.tsx', import.meta.url), 'utf8')
const clientSource = await readFile(new URL('../src/api/client.ts', import.meta.url), 'utf8')
const typesSource = await readFile(new URL('../src/api/types.ts', import.meta.url), 'utf8')

test('every exact-bib search requests Result Inquiry regardless of public row count or other filters', () => {
  assert.equal(shouldRequestResultInquiry({ bib: '101' }), true)
  assert.equal(shouldRequestResultInquiry({ name: 'Иван Иванов' }), false)
  assert.equal(shouldRequestResultInquiry({ bib: '101', name: 'Иван' }), true)
  assert.equal(shouldRequestResultInquiry({ bib: '101', gender: 'male' }), true)
  assert.equal(shouldRequestResultInquiry({ bib: '101', status: 'finished' }), true)
  assert.match(eventResultsSource, /loadPublicResultsAndInquiry\([\s\S]*api\.results[\s\S]*api\.resultInquiry/)
  assert.doesNotMatch(eventResultsSource, /shouldRequestResultInquiry\(loadedProtocol\.content\.length/)
})

test('exact-bib orchestration has the same ordered requests for zero, one, or many public rows', async () => {
  for (const publicRowCount of [0, 1, 3]) {
    const calls = []
    const loaded = await loadPublicResultsAndInquiry(
      { bib: '1100' },
      async () => { calls.push('public-results'); return { content: Array(publicRowCount).fill({}) } },
      async () => {
        calls.push('result-inquiry')
        return {
          lookupState: 'NEEDS_VERIFICATION', inquiryAvailability: 'OPEN', bib: '1100',
          missingResultActionAvailable: true,
        }
      },
    )
    assert.deepEqual(calls, ['public-results', 'result-inquiry'])
    assert.equal(loaded.publicResults.status, 'fulfilled')
    assert.equal(loaded.inquiry.status, 'fulfilled')
    if (loaded.publicResults.status === 'fulfilled') {
      assert.equal(loaded.publicResults.value.content.length, publicRowCount)
    }
    if (loaded.inquiry.status === 'fulfilled') {
      assert.equal(loaded.inquiry.value?.lookupState, 'NEEDS_VERIFICATION')
    }
  }
})

test('exact-bib starts public Results and Result Inquiry before either response completes', async () => {
  const calls = []
  let resolveResults
  let resolveInquiry
  const resultsResponse = new Promise((resolve) => { resolveResults = resolve })
  const inquiryResponse = new Promise((resolve) => { resolveInquiry = resolve })
  let completed = false
  const loading = loadPublicResultsAndInquiry(
    { bib: '1100' },
    () => { calls.push('public-results'); return resultsResponse },
    () => { calls.push('result-inquiry'); return inquiryResponse },
  ).then((outcome) => { completed = true; return outcome })

  assert.deepEqual(calls, ['public-results', 'result-inquiry'])
  resolveResults({ content: [{ resultId: 1 }] })
  await Promise.resolve()
  assert.equal(completed, false)
  resolveInquiry({
    lookupState: 'RESULT_PUBLIC', inquiryAvailability: 'OPEN', bib: '1100',
    missingResultActionAvailable: false,
  })
  const loaded = await loading
  assert.equal(loaded.publicResults.status, 'fulfilled')
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
  if (loaded.inquiry.status === 'fulfilled') assert.equal(loaded.inquiry.value, null)
})

test('latest request gate ignores out-of-order bib responses in both mixed completion orders', async () => {
  for (const order of ['results-second-first', 'inquiry-second-first']) {
    const gate = new LatestRequestGate()
    const firstResults = deferred()
    const firstInquiry = deferred()
    const secondResults = deferred()
    const secondInquiry = deferred()
    const firstId = gate.begin()
    const firstSearch = loadPublicResultsAndInquiry(
      { bib: '1100' }, () => firstResults.promise, () => firstInquiry.promise,
    )
    const secondId = gate.begin()
    const secondSearch = loadPublicResultsAndInquiry(
      { bib: '1200' }, () => secondResults.promise, () => secondInquiry.promise,
    )
    const applied = []
    const observeFirst = firstSearch.then(() => { if (gate.isCurrent(firstId)) applied.push('1100') })
    const observeSecond = secondSearch.then(() => { if (gate.isCurrent(secondId)) applied.push('1200') })

    if (order === 'results-second-first') {
      secondResults.resolve({ content: [{ bib: '1200' }] })
      firstInquiry.resolve({ lookupState: 'NEEDS_VERIFICATION' })
      secondInquiry.resolve({ lookupState: 'RESULT_PUBLIC' })
      firstResults.resolve({ content: [{ bib: '1100' }] })
    } else {
      secondInquiry.resolve({ lookupState: 'RESULT_PUBLIC' })
      firstResults.resolve({ content: [{ bib: '1100' }] })
      secondResults.resolve({ content: [{ bib: '1200' }] })
      firstInquiry.resolve({ lookupState: 'NEEDS_VERIFICATION' })
    }
    await Promise.all([observeFirst, observeSecond])
    assert.deepEqual(applied, ['1200'])
  }
})

test('race or Event change invalidates the previous search identity and its loading completion', () => {
  const gate = new LatestRequestGate()
  const raceARequest = gate.begin()
  const raceBRequest = gate.begin()
  assert.equal(gate.isCurrent(raceARequest), false)
  assert.equal(gate.isCurrent(raceBRequest), true)
  gate.invalidate()
  assert.equal(gate.isCurrent(raceBRequest), false)
  assert.match(eventResultsSource, /if \(isCurrentRequest\(\)\) \{\s*setLoading\(false\)/)
  assert.match(eventResultsSource, /event\.id, raceId/)
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

  const resultsSucceeded = resolvePublicResultsAndInquiry({
    publicResults: { status: 'fulfilled', value: { content: [{ resultId: 7 }] } },
    inquiry: { status: 'rejected', reason: networkError },
  })
  assert.equal(resultsSucceeded.state, 'PUBLIC_RESULTS_LOADED')
  assert.deepEqual(resultsSucceeded.publicResults.content, [{ resultId: 7 }])
  assert.equal(resultsSucceeded.inquiry, null)
  assert.equal(resultsSucceeded.inquiryError, networkError)

  const resultsFailed = resolvePublicResultsAndInquiry({
    publicResults: { status: 'rejected', reason: networkError },
    inquiry: { status: 'fulfilled', value: { lookupState: 'RESULT_NOT_PUBLIC' } },
  })
  assert.equal(resultsFailed.state, 'PUBLIC_RESULTS_FAILED')
  assert.equal(resultsFailed.error, networkError)
  assert.match(eventResultsSource, /!isRequestCancellation\(reason\)/)
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

test('Russian DOB input converts to an exact valid ISO date', () => {
  assert.equal(parseRussianBirthDate('10.01.1990'), '1990-01-10')
  assert.equal(parseRussianBirthDate('29.02.2024'), '2024-02-29')
  assert.equal(parseRussianBirthDate('29.02.2023'), null)
  assert.equal(parseRussianBirthDate('31.04.1990'), null)
  assert.equal(parseRussianBirthDate('1990-01-10'), null)
})

test('OPEN exposes CTA and uses the backend deadline value', () => {
  const view = availabilityView('OPEN', '2026-09-27T21:00:00Z', 'Europe/Moscow')
  assert.equal(view.canContact, true)
  assert.equal(view.statusText, 'Результат не установлен')
  assert.equal(view.deadlineText, 'Обратиться можно до 28.09.2026')
  assert.equal(formatInquiryDeadline('bad-value', 'Europe/Moscow'), null)
})

test('CLOSED, NOT_OPEN_YET and DISABLED never expose the CTA', () => {
  const closed = availabilityView('CLOSED', undefined, 'Europe/Moscow')
  const notOpen = availabilityView('NOT_OPEN_YET', undefined, 'Europe/Moscow')
  const disabled = availabilityView('DISABLED', undefined, 'Europe/Moscow')
  assert.equal(closed.canContact, false)
  assert.match(closed.explanation ?? '', /завершён/)
  assert.equal(notOpen.canContact, false)
  assert.match(notOpen.explanation ?? '', /после завершения мероприятия/)
  assert.equal(disabled.canContact, false)
  assert.equal(disabled.explanation, null)
})

test('email templates contain markers and Event, Start and bib facts', () => {
  const [missing, question] = createInquiryEmailTemplates('Контрольный забег', 'Индивидуальный · 5 км', '1100')
  assert.match(missing.subject, /^\[RESULT_MISSING\]/)
  assert.match(question.subject, /^\[RESULT_QUESTION\]/)
  for (const template of [missing, question]) {
    assert.match(template.subject, /Контрольный забег/)
    assert.match(template.subject, /Индивидуальный · 5 км/)
    assert.match(template.subject, /№1100/)
    assert.match(template.body, /Мероприятие: Контрольный забег/)
    assert.match(template.body, /Старт: Индивидуальный · 5 км/)
    assert.match(template.body, /Стартовый номер: 1100/)
    assert.match(template.body, /Дата рождения:\n/)
    assert.doesNotMatch(template.body, /10\.01\.1990|1990-01-10/)
  }
})

test('mailto keeps recipient as an email and encodes subject and body query values', () => {
  const [template] = createInquiryEmailTemplates('Забег №1', '5 км', 'A 1')
  const mailto = buildInquiryMailto('timing+result@example.test', template)
  assert.match(mailto, /^mailto:timing\+result@example\.test\?subject=/)
  assert.doesNotMatch(mailto, /^mailto:[^?]*%40/)
  assert.match(mailto, new RegExp(`subject=${encodeURIComponent(template.subject)}`))
  assert.match(mailto, new RegExp(`body=${encodeURIComponent(template.body)}`))
  assert.match(mailto, /%5BRESULT_MISSING%5D/)
  assert.match(mailto, /%E2%84%96/)
  assert.match(mailto, /%0A/)
  assert.doesNotMatch(mailto, /\s/)
})

test('DOB verification API sends personal data only in a POST JSON body', async () => {
  const calls = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (input, init) => {
    calls.push({ input: String(input), init })
    return new Response(JSON.stringify({
      lookupState: 'VERIFICATION_FAILED', inquiryAvailability: 'OPEN', bib: '1100',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  try {
    const { api } = await import(`../src/api/client.ts?result-inquiry=${Date.now()}`)
    await api.resultInquiry(7, 'A 1')
    await api.verifyResultInquiry(7, { bib: '1100', birthDate: '1990-01-10' })
  } finally {
    globalThis.fetch = originalFetch
  }
  assert.equal(calls.length, 2)
  assert.equal(calls[0].input, '/api/events/7/result-inquiry?bib=A+1')
  assert.equal(calls[0].init?.method, undefined)
  assert.equal(calls[1].input, '/api/events/7/result-inquiry/verify')
  assert.equal(calls[1].init?.method, 'POST')
  assert.equal(calls[1].init?.body, '{"bib":"1100","birthDate":"1990-01-10"}')
  assert.doesNotMatch(calls[1].input, /1990|birthDate/)
})

test('typed API states remain explicit rather than scattered booleans', () => {
  assert.match(typesSource, /'NEEDS_VERIFICATION'/)
  assert.match(typesSource, /'VERIFICATION_FAILED'/)
  assert.match(typesSource, /'RESULT_PUBLIC'/)
  assert.match(typesSource, /'RESULT_NOT_PUBLIC'/)
  assert.match(typesSource, /'DISABLED' \| 'NOT_OPEN_YET' \| 'OPEN' \| 'CLOSED'/)
  assert.doesNotMatch(typesSource, /isMissing|needsDob|isClosed/)
})

test('RESULT_NOT_PUBLIC is a dedicated special row without sporting facts', () => {
  assert.match(panelSource, /className="inquiry-result-row"/)
  assert.match(panelSource, /Результат не установлен/)
  assert.match(panelSource, /Уточнить результат/)
  assert.doesNotMatch(panelSource, /gunTime|chipTime|officialPlace|RankingAchievements|category/)
})

test('NEEDS_VERIFICATION renders neutral controls without candidates', () => {
  assert.match(panelSource, /Если вы не нашли свой результат, уточните данные/)
  assert.match(panelSource, /Уточните данные участника/)
  assert.match(panelSource, /placeholder="ДД\.ММ\.ГГГГ"/)
  assert.match(panelSource, /Не удалось подтвердить данные участника/)
  assert.doesNotMatch(panelSource, /candidateCount|firstName|lastName|birthDate.*inquiry/)
})

test('mixed public and non-public results coexist with one neutral follow-up action', () => {
  assert.match(eventResultsSource, /protocol\.content\.map\(\(result\)/)
  assert.match(eventResultsSource, /inquiry\.missingResultActionAvailable && <ResultInquiryPanel/)
  assert.match(eventResultsSource, /publicResultsPresent/)
  assert.match(panelSource, /publicResultsPresent && inquiry\.lookupState === 'NEEDS_VERIFICATION'/)
  assert.match(panelSource, /Если вы не нашли свой результат, уточните данные\./)
  assert.match(panelSource, />Уточнить результат<\/button>/)
  assert.doesNotMatch(panelSource, /несколько регистрац|несколько участник|дубликат номера|количество совпадений/i)
})

test('all-public duplicates keep all result rows and suppress the missing-result action', () => {
  assert.match(eventResultsSource, /key=\{result\.resultId\}/)
  assert.doesNotMatch(eventResultsSource, /key=\{result\.bib\}/)
  assert.match(eventResultsSource, /inquiry\.missingResultActionAvailable && <ResultInquiryPanel/)
  assert.doesNotMatch(eventResultsSource, /slice\(0,\s*[12]\)|content\[0\].*bib|===\s*2/)
})

test('mixed DOB verification opens missing flow only for a verified non-public registration', () => {
  assert.match(eventResultsSource, /verified\.lookupState === 'RESULT_PUBLIC'[\s\S]*setInquiry\(null\)/)
  assert.match(eventResultsSource, /verified\.lookupState === 'RESULT_NOT_PUBLIC' \? birthDate : null/)
  assert.match(panelSource, /publicResultsPresent && inquiry\.lookupState === 'RESULT_NOT_PUBLIC' && verifiedBirthDate !== null/)
  assert.match(panelSource, /kind="MISSING_RESULT"/)
})

test('DOB is not written to URL, browser storage, email template or frontend logs', () => {
  const combinedSource = `${eventResultsSource}\n${panelSource}\n${dialogSource}\n${clientSource}`
  assert.doesNotMatch(combinedSource, /localStorage|sessionStorage/)
  assert.doesNotMatch(clientSource, /result-inquiry\/verify\?/)
  assert.doesNotMatch(dialogSource, /message:.*birthDate|setMessage\([^)]*birthDate/)
  assert.match(dialogSource, /setBirthDate\(''\)/)
  assert.doesNotMatch(eventResultsSource, /console\.(?:log|info).*birth/i)
})

test('changing filters and Event clears inquiry-specific state', () => {
  assert.match(eventResultsSource, /const clearInquiryState = \(\) =>/)
  assert.match(eventResultsSource, /setInquiry\(null\)/)
  assert.match(eventResultsSource, /setVerificationError\(null\)/)
  assert.match(eventResultsSource, /setBib\(''\)/)
  assert.match(eventResultsSource, /onChange=\{\(changeEvent\) => \{ clearInquiryState\(\); setName/)
})

test('RESULT_PUBLIC reuses the ordinary race-scoped protocol', () => {
  assert.match(eventResultsSource, /lookup\.lookupState === 'RESULT_PUBLIC'/)
  assert.match(eventResultsSource, /if \(String\(lookup\.raceId\) === raceId\) return/)
  assert.match(eventResultsSource, /setRaceId\(String\(targetRace\.id\)\)/)
  assert.match(eventResultsSource, /setInquiry\(null\)/)
  assert.doesNotMatch(panelSource, /ResultListItem|setProtocol|resultId.*fake/i)
})

test('issue modal supports ESC, backdrop, focus restoration and a keyboard focus trap', () => {
  assert.match(dialogSource, /event\.key === 'Escape'/)
  assert.match(dialogSource, /previouslyFocused\?\.focus\(\)/)
  assert.match(dialogSource, /onMouseDown=\{closeDialog\}/)
  assert.match(dialogSource, /event\.key !== 'Tab'/)
  assert.match(dialogSource, /textarea:not\(\[disabled\]\)/)
  assert.doesNotMatch(dialogSource, /alert\(/)
})
